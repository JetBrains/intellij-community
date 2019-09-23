// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.tracing;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

public class MethodTracer {
    private final String myId;
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

    private MethodTracer(String id, String className, String methodName) {
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

    private static final MyConcurrentMap<String, MethodTracer> tracersMap = new MyConcurrentMap<>();

    public static MethodTracer getInstance(String id, String className, String methodName) {
        return isEnabled() ? tracersMap.putIfAbsent(id, new MethodTracer(id, className, methodName)) : empty;
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

    private static final MethodTracer empty = new MethodTracer("$$emptyTracer$$", "", "") {

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

        public <T> List<T> map(BiFunction<? super K, ? super V, T> function) {
            Map<K, V> map = myReference.get();
            ArrayList<T> result = new ArrayList<>(map.size());

            map.forEach((k, v) -> result.add(function.apply(k, v)));

            return result;
        }
    }

}
