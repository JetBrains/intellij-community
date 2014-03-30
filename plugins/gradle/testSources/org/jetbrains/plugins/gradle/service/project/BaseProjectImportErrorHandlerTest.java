/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.service.project;

import org.gradle.api.internal.LocationAwareException;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author Vladislav.Soroka
 * @since 10/16/13
 */
public class BaseProjectImportErrorHandlerTest {
  private BaseProjectImportErrorHandler myErrorHandler;
  private String myProjectPath;

  @Before
  public void setUp() throws Exception {
    myErrorHandler = new BaseProjectImportErrorHandler();
    myProjectPath = "basic";
  }

  @Test
  public void testGetUserFriendlyError() {
    String causeMsg = "failed to find target current";
    RuntimeException rootCause = new IllegalStateException(causeMsg);
    String locationMsg = "Build file '~/project/build.gradle' line: 86";

    RuntimeException locationError = new RuntimeException(locationMsg, rootCause) {
      @NotNull
      @Override
      public String toString() {
        return LocationAwareException.class.getName() + ": " + super.toString();
      }
    };

    Throwable error = new Throwable(locationError);

    //noinspection ThrowableResultOfMethodCallIgnored
    RuntimeException realCause = myErrorHandler.getUserFriendlyError(error, myProjectPath, null);
    String actualMsg = realCause.getMessage();
    assertTrue(actualMsg.contains(locationMsg));
    assertTrue(actualMsg.contains("Cause: " + causeMsg));
  }

  @Test
  public void testGetUserFriendlyErrorWithClassNotFoundException() {
    String causeMsg = "com.mypackage.MyImaginaryClass";
    ClassNotFoundException rootCause = new ClassNotFoundException(causeMsg);
    Throwable error = new Throwable(rootCause);
    //noinspection ThrowableResultOfMethodCallIgnored
    RuntimeException realCause = myErrorHandler.getUserFriendlyError(error, myProjectPath, null);
    assertTrue(realCause.getMessage().contains("Unable to load class 'com.mypackage.MyImaginaryClass'."));
  }
}
