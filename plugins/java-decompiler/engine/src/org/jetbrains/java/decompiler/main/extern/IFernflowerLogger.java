/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.main.extern;

public abstract class IFernflowerLogger {

  public enum Severity {
    TRACE("TRACE: "), INFO("INFO:  "), WARN("WARN:  "), ERROR("ERROR: ");

    public final String prefix;

    Severity(String prefix) {
      this.prefix = prefix;
    }
  }

  private Severity severity = Severity.INFO;

  public boolean accepts(Severity severity) {
    return severity.ordinal() >= this.severity.ordinal();
  }

  public void setSeverity(Severity severity) {
    this.severity = severity;
  }

  public abstract void writeMessage(String message, Severity severity);

  public abstract void writeMessage(String message, Severity severity, Throwable t);

  public void writeMessage(String message, Throwable t) {
    writeMessage(message, Severity.ERROR, t);
  }

  public void startReadingClass(String className) { }

  public void endReadingClass() { }

  public void startClass(String className) { }

  public void endClass() { }

  public void startMethod(String methodName) { }

  public void endMethod() { }

  public void startWriteClass(String className) { }

  public void endWriteClass() { }
}
