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
package git4idea;

import org.jetbrains.annotations.Nullable;

/**
 * Result of some operation.
 * Encapsulates both information about successfulness of the operation, and error details in the case of failure.
 *
 * @author Kirill Likhodedov
 */
public class Result {

  public static final Result SUCCESS = new Result(null);
  public static final Result CANCEL = new Result("Cancelled by user");

  @Nullable private final String myErrorDetails;

  public Result(@Nullable String errorDetails) {
    myErrorDetails = errorDetails;
  }

  public static Result error(String details) {
    return new Result(details);
  }

  @Nullable
  public String getErrorDetails() {
    return myErrorDetails;
  }

  public boolean isSuccess() {
    return this.equals(SUCCESS);
  }
}
