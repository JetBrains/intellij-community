/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class MessageBuildingSdkLog implements ILogger {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.sdk.MessageBuildingSdkLog");

  private final StringBuilder builder = new StringBuilder();

  public void warning(String warningFormat, Object... args) {
    if (warningFormat != null) {
      LOG.info(String.format(warningFormat, args));
    }
  }

  @Override
  public void info(@NonNull String msgFormat, Object... args) {
    if (msgFormat != null) {
      LOG.debug(String.format(msgFormat, args));
    }
  }

  @Override
  public void verbose(@NonNull String msgFormat, Object... args) {
  }

  public void error(Throwable t, String errorFormat, Object... args) {
    if (t != null) {
      LOG.info(t);
    }
    if (errorFormat != null) {
      String message = String.format(errorFormat, args);
      LOG.info(message);
      builder.append(message).append('\n');
    }
  }

  @NotNull
  public String getErrorMessage() {
    if (builder.length() > 0) {
      builder.delete(builder.length() - 1, builder.length());
    }
    return builder.toString();
  }
}
