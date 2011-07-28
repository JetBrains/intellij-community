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
 * Date: Nov 1, 2006
 * Time: 11:49:58 PM
 */
package com.intellij.util.messages;

import java.lang.reflect.Method;

/**
 * Defines contract for generic messages subscriber processor.
 */
public interface MessageHandler {

  /**
   * Is called on new message arrival. Given method identifies method used by publisher (see {@link Topic#getListenerClass()}),
   * given parameters were used by the publisher during target method call.
   *
   * @param event   information about target method called by the publisher
   * @param params  called method arguments
   */
  void handle(Method event, Object... params);
}