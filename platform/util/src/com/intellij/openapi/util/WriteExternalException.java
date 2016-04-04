/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.util;

/**
 * Throw this exception from {@link JDOMExternalizable#writeExternal(org.jdom.Element)} method if you don't want to store any settings.
 * If you simply return from the method empty '<component name=... />' tag will be written leading to unneeded modification of configuration files.
 */
public class WriteExternalException extends RuntimeException {
  public WriteExternalException() {
    super();
  }

  public WriteExternalException(String s) {
    super(s);
  }

  public WriteExternalException(String message, Throwable cause) {
    super(message, cause);
  }

  public WriteExternalException(Throwable cause) {
    super(cause);
  }
}
