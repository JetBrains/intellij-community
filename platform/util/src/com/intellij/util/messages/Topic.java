/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 22, 2006
 * Time: 9:49:16 PM
 */
package com.intellij.util.messages;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Defines messaging endpoint within particular {@link MessageBus bus}.
 *
 * @param <L>  type of the interface that defines contract for working with the particular topic instance
 */
public class Topic<L> {
  private final String myDisplayName;
  private final Class<L> myListenerClass;
  private final BroadcastDirection myBroadcastDirection;

  public Topic(@NonNls @NotNull String displayName, @NotNull Class<L> listenerClass) {
    this(displayName, listenerClass, BroadcastDirection.TO_CHILDREN);
  }

  public Topic(@NonNls @NotNull String displayName, @NotNull Class<L> listenerClass, final BroadcastDirection broadcastDirection) {
    myDisplayName = displayName;
    myListenerClass = listenerClass;
    myBroadcastDirection = broadcastDirection;
  }

  /**
   * @return    human-readable name of the current topic. Is intended to be used in informational/logging purposes only
   */
  @NotNull
  @NonNls
  public String getDisplayName() {
    return myDisplayName;
  }

  /**
   * Allows to retrieve class that defines contract for working with the current topic. Either publishers or subscribers use it:
   * <ul>
   *   <li>
   *     publisher {@link MessageBus#syncPublisher(Topic) receives} object that IS-A target interface from the messaging infrastructure.
   *     It calls target method with the target arguments on it then (method of the interface returned by the current method);
   *   </li>
   *   <li>
   *     the same method is called on handlers of all {@link MessageBusConnection#subscribe(Topic, Object) subscribers} that
   *     should receive the message;
   *   </li>
   * </ul>
   *
   * @return    class of the interface that defines contract for working with the current topic
   */
  @NotNull
  public Class<L> getListenerClass() {
    return myListenerClass;
  }

  public String toString() {
    return myDisplayName;
  }

  public static <L> Topic<L> create(@NonNls @NotNull String displayName, @NotNull Class<L> listenerClass) {
    return new Topic<L>(displayName, listenerClass);
  }

  public static <L> Topic<L> create(@NonNls @NotNull String displayName, @NotNull Class<L> listenerClass, BroadcastDirection direction) {
    return new Topic<L>(displayName, listenerClass, direction);
  }

  /**
   * @return    broadcasting strategy configured for the current topic. Default value is {@link BroadcastDirection#TO_CHILDREN}
   * @see BroadcastDirection
   */
  public BroadcastDirection getBroadcastDirection() {
    return myBroadcastDirection;
  }

  /**
   * {@link MessageBus Message buses} may be organised into {@link MessageBus#getParent() hierarchies}. That allows to provide
   * additional messaging features like <code>'broadcasting'</code>. Here it means that messages sent to particular topic within
   * particular message bus may be dispatched to subscribers of the same topic within another message buses.
   * <p/>
   * Current enum holds available broadcasting options.
   */
  public enum BroadcastDirection {

    /**
     * The message is dispatched to all subscribers of the target topic registered within the child message buses.
     * <p/>
     * Example:
     * <pre>
     *                         parent-bus &lt;--- topic1
     *                          /       \
     *                         /         \
     *    topic1 ---&gt; child-bus1     child-bus2 &lt;--- topic1
     * </pre>
     * <p/>
     * Here subscribers of the <code>'topic1'</code> registered within the <code>'child-bus1'</code> and <code>'child-bus2'</code>
     * will receive the message sent to the <code>'topic1'</code> topic at the <code>'parent-bus'</code>.
     */
    TO_CHILDREN,

    /**
     * No broadcasting is performed for the
     */
    NONE,

    /**
     * The message send to particular topic at particular bus is dispatched to all subscribers of the same topic within the parent bus.
     * <p/>
     * Example:
     * <pre>
     *           parent-bus &lt;--- topic1
     *                |
     *            child-bus &lt;--- topic1
     * </pre>
     * <p/>
     * Here subscribers of the <code>topic1</code> registered within <code>'parent-bus'</code> will receive messages posted
     * to the same topic within <code>'child-bus'</code>.
     */
    TO_PARENT
  }
}