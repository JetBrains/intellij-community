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

public class InvalidDataException extends RuntimeException {
  public InvalidDataException() {
    super();
  }

  public InvalidDataException(String s) {
    super(s);
  }

  public InvalidDataException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidDataException(Throwable cause) {
    super(cause);
  }
}
