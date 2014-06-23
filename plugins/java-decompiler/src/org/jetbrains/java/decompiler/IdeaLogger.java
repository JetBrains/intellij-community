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
package org.jetbrains.java.decompiler;

import com.intellij.openapi.diagnostic.Logger;
import de.fernflower.main.extern.IFernflowerLogger;

public class IdeaLogger implements IFernflowerLogger {
  private final static Logger LOG = Logger.getInstance(IdeaDecompiler.class);

  public static class InternalException extends RuntimeException {
    public InternalException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  @Override
  public void writeMessage(String message, int severity) {
    if (severity >= ERROR) LOG.error(message);
    else if (severity == WARNING) LOG.warn(message);
    else if (severity == INFO) LOG.info(message);
    else LOG.debug(message);
  }

  @Override
  public void writeMessage(String message, Throwable t) {
    if (t instanceof InternalException) throw (InternalException)t;
    else throw new InternalException(message, t);
  }

  @Override
  public void startClass(String className) {
    LOG.debug("processing class " + className);
  }

  @Override
  public void endClass() {
    LOG.debug("... class processed");
  }

  @Override
  public void startWriteClass(String className) {
    LOG.debug("writing class " + className);
  }

  @Override
  public void endWriteClass() {
    LOG.debug("... class written");
  }

  @Override
  public void startMethod(String method) {
    LOG.debug("processing method " + method);
  }

  @Override
  public void endMethod() {
    LOG.debug("... method processed");
  }

  @Override
  public int getSeverity() {
    return WARNING;
  }

  @Override
  public void setSeverity(int ignore) { }
}
