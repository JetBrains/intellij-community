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
package com.intellij.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Irina.Chernushina on 6/20/2017.
 */
public class ResultWithError<Result, Error> {
  @SuppressWarnings("unchecked") private static final ResultWithError EMPTY = new ResultWithError(null, null);
  @Nullable private final Result myResult;
  @Nullable private final Error myError;

  private ResultWithError(@Nullable final Result result, @Nullable final Error error) {
    myResult = result;
    myError = error;
  }

  public static <R, E> ResultWithError<R, E> result(@NotNull R result) {
    return new ResultWithError<R, E>(result, null);
  }

  public static <R, E> ResultWithError<R, E> error(@NotNull E error) {
    return new ResultWithError<R, E>(null, error);
  }

  public static <R, E> ResultWithError<R, E> empty() {
    //noinspection unchecked
    return (ResultWithError<R, E>)EMPTY;
  }

  @Nullable
  public Result getResult() {
    return myResult;
  }

  @Nullable
  public Error getError() {
    return myError;
  }
}
