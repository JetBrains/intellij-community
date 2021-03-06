// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.tracing;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

public class MethodTracer {
    private final @NotNull TracerId myId;
    private final String myClassName;
    private final String myMethodName;

    private final AtomicLong myInvocationCount   = new AtomicLong(0);
    private final AtomicLong myNonRecursiveCount = new AtomicLong(0);
    private final AtomicLong myTotalTime         = new AtomicLong(0);
    private final AtomicLong myMaxTime           = new AtomicLong(0);

    private final AtomicLong myTimeOnEdt         = new AtomicLong(0);
    private final AtomicLong myCountOnEdt        = new AtomicLong(0);
    private final AtomicLong myMaxTimeOnEdt      = new AtomicLong(0);

    private final AtomicInteger myMaxRecursionDepth = new AtomicInteger(0);

    private final ThreadLocal<Long>    currentCalculationStart = ThreadLocal.withInitial(() -> null);
    private final ThreadLocal<Integer> currentRecursionDepth   = ThreadLocal.withInitial(() -> 0);

    private MethodTracer(@NotNull TracerId id, @NonNls String className, @NonNls String methodName) {
        myId = id;
        myClassName = className;
        myMethodName = methodName;
    }

    public void onMethodEnter() {
        myInvocationCount.incrementAndGet();
        Integer recursionOnStart = currentRecursionDepth.get();

        myMaxRecursionDepth.updateAndGet(old -> Math.max(old, recursionOnStart));

        if (recursionOnStart == 0) {
            currentCalculationStart.set(System.nanoTime());
            myNonRecursiveCount.incrementAndGet();
        }

        currentRecursionDepth.set(recursionOnStart + 1);
    }

    public void onMethodExit() {
        currentRecursionDepth.set(currentRecursionDepth.get() - 1);

        if (currentRecursionDepth.get() == 0) {
            long duration = System.nanoTime() - currentCalculationStart.get();
            currentCalculationStart.set(null);

            myTotalTime.addAndGet(duration);
            myMaxTime.updateAndGet(old -> Math.max(old, duration));

            if (isDispatchThread.get()) {
                myCountOnEdt.incrementAndGet();
                myTimeOnEdt.addAndGet(duration);
                myMaxTimeOnEdt.updateAndGet(old -> Math.max(old, duration));
            }
        }
    }

    public MethodTracerData getCurrentData() {
        return new MethodTracerData(myId,
                                    myClassName,
                                    myMethodName,
                                    myInvocationCount.get(),
                                    myNonRecursiveCount.get(),
                                    toMillis(myTotalTime.get()),
                                    toMillis(myMaxTime.get()),
                                    myCountOnEdt.get(),
                                    toMillis(myTimeOnEdt.get()),
                                    toMillis(myMaxTimeOnEdt.get()),
                                    myMaxRecursionDepth.get());
    }

    private static long toMillis(long time) {
        return TimeUnit.NANOSECONDS.toMillis(time);
    }

    private static final MyConcurrentMap<TracerId, MethodTracer> tracersMap = new MyConcurrentMap<>();

    /**
     * @deprecated Used in 2020.2 performancePlugin
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
    public static MethodTracer getInstance(@NotNull String tracerId,
                                           @NotNull String simpleClassName,
                                           @NotNull String presentableMethodName) {
        TracerId id = new TracerId(tracerId, "", null, "");
        return isEnabled() ? tracersMap.putIfAbsent(id, new MethodTracer(id, simpleClassName, presentableMethodName)) : empty;
    }

    public static MethodTracer getInstance(@NotNull String className,
                                           @NotNull String simpleClassName,
                                           @NotNull String methodName,
                                           @Nullable String methodNameSuffix,
                                           @NotNull String desc) {
        TracerId id = new TracerId(className, methodName, methodNameSuffix, desc);
        String presentableMethodName = methodName;
        if (methodNameSuffix != null) {
            presentableMethodName = methodName + "-" + methodNameSuffix;
        }
        return isEnabled() ? tracersMap.putIfAbsent(id, new MethodTracer(id, simpleClassName, presentableMethodName)) : empty;
    }

    private static boolean enabled = true;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        MethodTracer.enabled = enabled;
    }

    public static void clearAll() {
        tracersMap.clear();
    }

    public static List<MethodTracerData> getAllData() {
        return tracersMap.map((k, v) -> v.getCurrentData());
    }

    private static final MethodTracer empty = new MethodTracer(TracerId.EMPTY, "", "") {

        @Override
        public void onMethodEnter() {}

        @Override
        public void onMethodExit() {}

        @Override
        public MethodTracerData getCurrentData() {
            throw new UnsupportedOperationException();
        }
    };

    private static final ThreadLocal<Boolean> isDispatchThread = ThreadLocal.withInitial(() -> SwingUtilities.isEventDispatchThread());

    //atomic reference to an immutable map, it has better read performance than ConcurrentHashMap
    private static class MyConcurrentMap<K, V> {

        private final AtomicReference<Map<K, V>> myReference = new AtomicReference<>(Collections.emptyMap());

        public V putIfAbsent(K key, V value) {
            do {
                Map<K, V> prevMap = myReference.get();
                V existingValue = prevMap.get(key);

                if (existingValue != null) {
                    return existingValue;
                }

                Map<K, V> newMap = new HashMap<>(prevMap);
                newMap.put(key, value);

                if (myReference.compareAndSet(prevMap, Collections.unmodifiableMap(newMap))) {
                    return value;
                }
            } while(true);
        }

        public void clear() {
            myReference.set(Collections.emptyMap());
        }

        public Collection<V> values() {
            return myReference.get().values();
        }

        public <T> List<T> map(BiFunction<? super K, ? super V, ? extends T> function) {
            Map<K, V> map = myReference.get();
            ArrayList<T> result = new ArrayList<>(map.size());

            map.forEach((k, v) -> result.add(function.apply(k, v)));

            return result;
        }
    }

    public static final class TracerId {

        public static final TracerId EMPTY = new TracerId("", "", "", "");

        private final String myClassName;
        private final String myMethodName;
        private final String mySuffix;
        private final String myDescription;

        public TracerId(@NotNull @NonNls String className,
                        @NotNull @NonNls String methodName,
                        @Nullable @NonNls String suffix,
                        @NotNull @NonNls String description) {

            myClassName = className;
            myMethodName = methodName;
            mySuffix = suffix;
            myDescription = description;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TracerId id = (TracerId)o;
            return myClassName.equals(id.myClassName) &&
                   myMethodName.equals(id.myMethodName) &&
                   Objects.equals(mySuffix, id.mySuffix) &&
                   myDescription.equals(id.myDescription);
        }

        @Override
        public int hashCode() {
            return Objects.hash(myClassName, myMethodName, mySuffix, myDescription);
        }
    }
}
