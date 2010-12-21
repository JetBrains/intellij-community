/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsEventsListenerManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.util.Consumer;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;

/**
 * @author irengrig
 *         Date: 12/15/10
 *         Time: 5:50 PM
 */
public class VcsEventsListenerManagerImpl implements VcsEventsListenerManager, VcsEnvironmentsProxyCreator {
  private final Wrapper<CheckinEnvironment> myCheckinWrapper;
  private final Wrapper<UpdateEnvironment> myUpdateWrapper;
  private final Wrapper<RollbackEnvironment> myRollbackWrapper;

  public VcsEventsListenerManagerImpl() {
    myCheckinWrapper = new Wrapper<CheckinEnvironment>(CheckinEnvironment.class);
    myUpdateWrapper = new Wrapper<UpdateEnvironment>(UpdateEnvironment.class);
    myRollbackWrapper = new Wrapper<RollbackEnvironment>(RollbackEnvironment.class);
  }

  @Override
  public Object addCheckin(final Consumer<Pair<VcsKey, Consumer<CheckinEnvironment>>> consumer) {
    return myCheckinWrapper.add(consumer);
  }

  @Override
  public Object addUpdate(final Consumer<Pair<VcsKey, Consumer<UpdateEnvironment>>> consumer) {
    return myUpdateWrapper.add(consumer);
  }

  @Override
  public Object addRollback(final Consumer<Pair<VcsKey, Consumer<RollbackEnvironment>>> consumer) {
    return myRollbackWrapper.add(consumer);
  }

  @Override
  public void removeCheckin(Object key) {
    myCheckinWrapper.remove(key);
  }

  @Override
  public void removeUpdate(Object key) {
    myUpdateWrapper.remove(key);
  }

  @Override
  public void removeRollback(Object key) {
    myRollbackWrapper.remove(key);
  }

  @Nullable
  @Override
  public CheckinEnvironment proxyCheckin(final VcsKey key, final CheckinEnvironment environment) {
    return myCheckinWrapper.createProxy(key, environment);
  }

  @Nullable
  @Override
  public UpdateEnvironment proxyUpdate(final VcsKey key, final UpdateEnvironment environment) {
    return myUpdateWrapper.createProxy(key, environment);
  }

  @Nullable
  @Override
  public RollbackEnvironment proxyRollback(final VcsKey key, final RollbackEnvironment environment) {
    return myRollbackWrapper.createProxy(key, environment);
  }

  private static class Wrapper<T> {
    private final Map<Object, EventListenerWrapperI> myListenersMap;
    private final Map<VcsKey, EventDispatcher<EventListenerWrapperI>> myExistingMulticasters;
    private final Class<T> myClazz;
    private final Object myLock;
    private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.VcsEventsListenerManagerImpl.Wrapper");

    private Wrapper(final Class<T> clazz) {
      myClazz = clazz;
      myListenersMap = new HashMap<Object, EventListenerWrapperI>();
      myExistingMulticasters = Collections.synchronizedMap(new HashMap<VcsKey, EventDispatcher<EventListenerWrapperI>>());
      myLock = new Object();
    }

    @Nullable
    public T createProxy(final VcsKey key, @Nullable final T environment) {
      if (environment == null) return null;
      final EventDispatcher<EventListenerWrapperI> eventDispatcher;
      synchronized (myLock) {
        assert ! myExistingMulticasters.containsKey(key);
        eventDispatcher = EventDispatcher.create(EventListenerWrapperI.class);
        myExistingMulticasters.put(key, eventDispatcher);
        for (EventListenerWrapperI wrapper : myListenersMap.values()) {
          eventDispatcher.addListener(wrapper);
        }
      }

      final T proxy = (T) Proxy.newProxyInstance(myClazz.getClassLoader(),
                                        new Class[]{myClazz},
                                        new InvocationHandler() {
                                          @Override
                                          public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
                                            method.setAccessible(true);
                                            synchronized (myLock) {
                                              eventDispatcher.getMulticaster().consume(
                                                new Pair<VcsKey, Consumer<T>>(key, new Consumer<T>() {
                                                  @Override
                                                  public void consume(T t) {
                                                    try {
                                                      method.invoke(t, args);
                                                    }
                                                    catch (IllegalAccessException e) {
                                                      LOG.info(e);
                                                    }
                                                    catch (InvocationTargetException e) {
                                                      LOG.info(e);
                                                    }
                                                  }
                                                }));
                                            }
                                            return method.invoke(environment, args);
                                          }
                                        });

      return proxy;
    }

    public Object add(final Consumer<Pair<VcsKey,Consumer<T>>> consumer) {
      final Object key = new Object();
      synchronized (myLock) {
        EventListenerWrapper<T> listenerWrapper = new EventListenerWrapper<T>(consumer);
        myListenersMap.put(key, listenerWrapper);
        for (EventDispatcher<EventListenerWrapperI> eventDispatcher : myExistingMulticasters.values()) {
          eventDispatcher.addListener(listenerWrapper);
        }
      }
      return key;
    }

    public void remove(Object key) {
      synchronized (myLock) {
        final EventListenerWrapperI listenerWrapper = myListenersMap.remove(key);
        if (listenerWrapper != null) {
          for (EventDispatcher<EventListenerWrapperI> dispatcher : myExistingMulticasters.values()) {
            dispatcher.removeListener(listenerWrapper);
          }
        }
      }
    }

    private interface EventListenerWrapperI<T> extends Consumer<Pair<VcsKey, Consumer<T>>>, EventListener {}

    private static class EventListenerWrapper<T> implements EventListenerWrapperI<T> {
      private final Consumer<Pair<VcsKey, Consumer<T>>> myConsumer;

      public EventListenerWrapper(Consumer<Pair<VcsKey, Consumer<T>>> consumer) {
        myConsumer = consumer;
      }

      @Override
      public void consume(Pair<VcsKey, Consumer<T>> vcsKeyConsumerPair) {
        myConsumer.consume(vcsKeyConsumerPair);
      }
    }
  }
}
