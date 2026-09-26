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
package pkg;

public abstract class TestDeprecations {
  /** @deprecated */
  public int byComment;

  @Deprecated
  public int byAnno;

  /** @deprecated */
  public void byComment() {
    int a =5;
  }

  /** @deprecated */
  public abstract void byCommentAbstract();

  @Deprecated
  public void byAnno() {
    int a =5;
  }

  @Deprecated
  public abstract void byAnnoAbstract();

  /** @deprecated */
  public static class ByComment {
    int a =5;

    void foo() {
      int x = 5;
    }
  }

  @Deprecated
  public static class ByAnno {
    int a =5;

    void foo() {
      int x = 5;
    }
  }
}