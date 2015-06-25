/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.messages.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author max
 */
public class MessageBusImpl implements MessageBus {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.messages.impl.MessageBusImpl");
  private static final Comparator<MessageBusImpl> MESSAGE_BUS_COMPARATOR = new Comparator<MessageBusImpl>() {
    @Override
    public int compare(MessageBusImpl bus1, MessageBusImpl bus2) {
      return ContainerUtil.compareLexicographically(bus1.myOrderRef.get(), bus2.myOrderRef.get());
    }
  };
  private final ThreadLocal<Queue<DeliveryJob>> myMessageQueue = createThreadLocalQueue();

  /**
   * Root's order is empty
   * Child bus's order is its parent order plus one more element, an int that's bigger than that of all sibling buses that come before
   * Sorting by these vectors lexicographically gives DFS order
   */
  private final AtomicReference<List<Integer>> myOrderRef = new AtomicReference<List<Integer>>();

  private final ConcurrentMap<Topic, Object> mySyncPublishers = new ConcurrentHashMap<Topic, Object>();
  private final ConcurrentMap<Topic, Object> myAsyncPublishers = new ConcurrentHashMap<Topic, Object>();

  /**
   * This bus's subscribers
   */
  private final ConcurrentMap<Topic, List<MessageBusConnectionImpl>> mySubscribers =
    new ConcurrentHashMap<Topic, List<MessageBusConnectionImpl>>();

  /**
   * Caches subscribers for this bus and its children or parent, depending on the topic's broadcast policy
   */
  private final ConcurrentMap<Topic, List<MessageBusConnectionImpl>> mySubscriberCache =
    new ConcurrentHashMap<Topic, List<MessageBusConnectionImpl>>();
  private final Deque<MessageBusImpl> myChildBuses = new LinkedBlockingDeque<MessageBusImpl>();
  private final Set<List<Integer>> myChildOrders = Collections.newSetFromMap(new ConcurrentHashMap<List<Integer>, Boolean>());

  private static final Object NA = new Object();
  private MessageBusImpl myParentBus;

  //is used for debugging purposes
  private final Object myOwner;
  private boolean myDisposed;

  public MessageBusImpl(@NotNull Object owner, @NotNull MessageBus parentBus) {
    myOwner = owner.toString() + " of " + owner.getClass();
    myParentBus = (MessageBusImpl)parentBus;
    myParentBus.onChildBusCreated(this, new Consumer<List<Integer>>() {
      @Override
      public void consume(List<Integer> integers) {
        myOrderRef.set(integers);
      }
    });
    LOG.assertTrue(myParentBus.myChildBuses.contains(this));
    LOG.assertTrue(myOrderRef.get() != null);
  }

  private MessageBusImpl(Object owner) {
    myOwner = owner.toString() + " of " + owner.getClass();
    myOrderRef.set(Collections.<Integer>emptyList());
  }

  @Override
  public MessageBus getParent() {
    return myParentBus;
  }

  @NotNull
  private RootBus getRootBus() {
    return myParentBus != null ? myParentBus.getRootBus() : asRoot();
  }

  private RootBus asRoot() {
    if (this instanceof RootBus) {
      return (RootBus)this;
    }
    throw new AssertionError("Accessing disposed message bus " + this);
  }

  @Override
  public String toString() {
    return super.toString() + "; owner=" + myOwner + (myDisposed ? "; disposed" : "");
  }

  private void onChildBusCreated(final MessageBusImpl childBus, @NotNull Consumer<List<Integer>> childOrderConsumer) {
    LOG.assertTrue(childBus.myParentBus == this);
    List<Integer> childOrder = new ArrayList<Integer>(myOrderRef.get().size() + 1);
    childOrder.addAll(myOrderRef.get());
    childOrder.add(1); // Dummy holder, just to be able to call set(index) later
    while (true) {
      final MessageBusImpl lastChild = myChildBuses.peekLast();
      final int lastChildIndex;
      if (lastChild == null) {
        lastChildIndex = 0;
      }
      else {
        final List<Integer> lastChildOrder = lastChild.myOrderRef.get();
        lastChildIndex = lastChildOrder.get(lastChildOrder.size() - 1);
      }
      if (lastChildIndex == Integer.MAX_VALUE) {
        LOG.error("Too many child buses");
      }
      childOrder.set(childOrder.size() - 1, lastChildIndex + 1);
      if (myChildOrders.add(childOrder)) {
        break;
      }
    }
    childOrderConsumer.consume(childOrder);
    myChildBuses.add(childBus);
    getRootBus().clearSubscriberCache();
  }

  private void notifyChildBusDisposed(final MessageBusImpl childBus) {
    boolean removed = myChildBuses.remove(childBus);
    myChildOrders.remove(childBus.myOrderRef.get());
    Map<MessageBusImpl, Integer> map = getRootBus().myWaitingBuses.get();
    if (map != null) map.remove(childBus);
    getRootBus().clearSubscriberCache();
    LOG.assertTrue(removed);
  }

  private static class DeliveryJob {
    public DeliveryJob(final MessageBusConnectionImpl connection, final Message message) {
      this.connection = connection;
      this.message = message;
    }

    public final MessageBusConnectionImpl connection;
    public final Message message;

    @NonNls
    @Override
    public String toString() {
      return "{ DJob connection:" + connection.toString() + "; message: " + message + " }";
    }
  }

  @Override
  @NotNull
  public MessageBusConnection connect() {
    checkNotDisposed();
    return new MessageBusConnectionImpl(this);
  }

  @Override
  @NotNull
  public MessageBusConnection connect(@NotNull Disposable parentDisposable) {
    final MessageBusConnection connection = connect();
    Disposer.register(parentDisposable, connection);
    return connection;
  }

  @Override
  @NotNull
  @SuppressWarnings({"unchecked"})
  public <L> L syncPublisher(@NotNull final Topic<L> topic) {
    checkNotDisposed();
    L publisher = (L)mySyncPublishers.get(topic);
    if (publisher == null) {
      final Class<L> listenerClass = topic.getListenerClass();
      InvocationHandler handler = new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          sendMessage(new Message(topic, method, args));
          return NA;
        }
      };
      publisher = (L)Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class[]{listenerClass}, handler);
      publisher = (L)ConcurrencyUtil.cacheOrGet(mySyncPublishers, topic, publisher);
    }
    return publisher;
  }

  @Override
  @NotNull
  @SuppressWarnings({"unchecked"})
  public <L> L asyncPublisher(@NotNull final Topic<L> topic) {
    checkNotDisposed();
    L publisher = (L)myAsyncPublishers.get(topic);
    if (publisher == null) {
      final Class<L> listenerClass = topic.getListenerClass();
      InvocationHandler handler = new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          postMessage(new Message(topic, method, args));
          return NA;
        }
      };
      publisher = (L)Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class[]{listenerClass}, handler);
      publisher = (L)ConcurrencyUtil.cacheOrGet(myAsyncPublishers, topic, publisher);
    }
    return publisher;
  }

  @Override
  public void dispose() {
    checkNotDisposed();
    Queue<DeliveryJob> jobs = myMessageQueue.get();
    if (!jobs.isEmpty()) {
      LOG.error("Not delivered events in the queue: " + jobs);
    }
    myMessageQueue.remove();
    if (myParentBus != null) {
      myParentBus.notifyChildBusDisposed(this);
      myParentBus = null;
    } else {
      asRoot().myWaitingBuses.remove();
    }
    myDisposed = true;
  }

  private void checkNotDisposed() {
    if (myDisposed) LOG.error("Already disposed: " + this);
  }

  private void calcSubscribers(Topic topic, List<MessageBusConnectionImpl> result) {
    final List<MessageBusConnectionImpl> topicSubscribers = mySubscribers.get(topic);
    if (topicSubscribers != null) {
      result.addAll(topicSubscribers);
    }

    Topic.BroadcastDirection direction = topic.getBroadcastDirection();

    if (direction == Topic.BroadcastDirection.TO_CHILDREN) {
      for (MessageBusImpl childBus : myChildBuses) {
        childBus.calcSubscribers(topic, result);
      }
    }

    if (direction == Topic.BroadcastDirection.TO_PARENT && myParentBus != null) {
      myParentBus.calcSubscribers(topic, result);
    }
  }

  private void postMessage(Message message) {
    checkNotDisposed();
    final Topic topic = message.getTopic();
    List<MessageBusConnectionImpl> topicSubscribers = mySubscriberCache.get(topic);
    if (topicSubscribers == null) {
      topicSubscribers = new SmartList<MessageBusConnectionImpl>();
      calcSubscribers(topic, topicSubscribers);
      mySubscriberCache.put(topic, topicSubscribers);
    }
    if (!topicSubscribers.isEmpty()) {
      for (MessageBusConnectionImpl subscriber : topicSubscribers) {
        subscriber.getBus().myMessageQueue.get().offer(new DeliveryJob(subscriber, message));
        subscriber.getBus().notifyPendingJobChange(1);
        subscriber.scheduleMessageDelivery(message);
      }
    }
  }

  private void notifyPendingJobChange(int delta) {
    ThreadLocal<SortedMap<MessageBusImpl, Integer>> ref = getRootBus().myWaitingBuses;
    SortedMap<MessageBusImpl, Integer> map = ref.get();
    if (map == null) {
      ref.set(map = new TreeMap<MessageBusImpl, Integer>(MESSAGE_BUS_COMPARATOR));
    }
    Integer countObject = map.get(this);
    int count = countObject == null ? 0 : countObject;
    int newCount = count + delta;
    if (newCount > 0) {
      checkNotDisposed();
      map.put(this, newCount);
    } else if (newCount == 0) {
      map.remove(this);
    } else {
      LOG.error("Negative job count: " + this);
    }
  }

  private void sendMessage(Message message) {
    pumpMessages();
    postMessage(message);
    pumpMessages();
  }

  private void pumpMessages() {
    checkNotDisposed();
    if (myParentBus != null) {
      LOG.assertTrue(myParentBus.myChildBuses.contains(this));
      myParentBus.pumpMessages();
    }
    else {
      Map<MessageBusImpl, Integer> map = asRoot().myWaitingBuses.get();
      if (map != null) {
        Set<MessageBusImpl> buses = map.keySet();
        if (!buses.isEmpty()) {
          for (MessageBusImpl bus : new ArrayList<MessageBusImpl>(buses)) {
            if (bus.myDisposed) {
              map.remove(bus);
              LOG.error("Accessing disposed message bus " + bus);
              continue;
            }

            bus.doPumpMessages();
          }
        }
      }
    }
  }

  private void doPumpMessages() {
    Queue<DeliveryJob> queue = myMessageQueue.get();
    do {
      DeliveryJob job = queue.poll();
      if (job == null) break;
      notifyPendingJobChange(-1);
      job.connection.deliverMessage(job.message);
    }
    while (true);
  }

  void notifyOnSubscription(final MessageBusConnectionImpl connection, final Topic topic) {
    checkNotDisposed();
    List<MessageBusConnectionImpl> topicSubscribers = mySubscribers.get(topic);
    if (topicSubscribers == null) {
      topicSubscribers = ContainerUtil.createLockFreeCopyOnWriteList();
      topicSubscribers = ConcurrencyUtil.cacheOrGet(mySubscribers, topic, topicSubscribers);
    }

    topicSubscribers.add(connection);
    getRootBus().clearSubscriberCache();
  }

  void clearSubscriberCache() {
    mySubscriberCache.clear();
    for (MessageBusImpl bus : myChildBuses) {
      bus.clearSubscriberCache();
    }
  }

  void notifyConnectionTerminated(final MessageBusConnectionImpl connection) {
    for (List<MessageBusConnectionImpl> topicSubscribers : mySubscribers.values()) {
      topicSubscribers.remove(connection);
    }
    if (myDisposed) return;
    getRootBus().clearSubscriberCache();

    final Iterator<DeliveryJob> i = myMessageQueue.get().iterator();
    while (i.hasNext()) {
      final DeliveryJob job = i.next();
      if (job.connection == connection) {
        i.remove();
        notifyPendingJobChange(-1);
      }
    }
  }

  void deliverSingleMessage() {
    checkNotDisposed();
    final DeliveryJob job = myMessageQueue.get().poll();
    if (job == null) return;
    notifyPendingJobChange(-1);
    job.connection.deliverMessage(job.message);
  }

  @NotNull
  static <T> ThreadLocal<Queue<T>> createThreadLocalQueue() {
    return new ThreadLocal<Queue<T>>() {
      @Override
      protected Queue<T> initialValue() {
        return new ConcurrentLinkedQueue<T>();
      }
    };
  }

  public static class RootBus extends MessageBusImpl {
    /**
     * Holds the counts of pending messages for all message buses in the hierarchy
     * This field is null for non-root buses
     * The map's keys are sorted by {@link #myOrderRef}
     *
     * Used to avoid traversing the whole hierarchy when there are no messages to be sent in most of it
     */
    private final ThreadLocal<SortedMap<MessageBusImpl, Integer>> myWaitingBuses = new ThreadLocal<SortedMap<MessageBusImpl, Integer>>();

    public RootBus(@NotNull Object owner) {
      super(owner);
    }
  }
}
