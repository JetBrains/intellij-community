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

package org.jetbrains.android.sdk;

import com.android.annotations.NonNull;
import com.android.utils.ILogger;

/**
* Created by IntelliJ IDEA.
* User: Eugene.Kudelevsky
* Date: Aug 15, 2009
* Time: 6:11:16 PM
* To change this template use File | Settings | File Templates.
*/
public class EmptySdkLog implements ILogger {
  public void warning(String warningFormat, Object... args) {
  }

  @Override
  public void info(@NonNull String msgFormat, Object... args) {
  }

  @Override
  public void verbose(@NonNull String msgFormat, Object... args) {
  }

  public void error(Throwable t, String errorFormat, Object... args) {
  }
}
