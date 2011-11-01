/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.history.wholeTree;

import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/26/11
 * Time: 7:16 PM
 */
public interface WireEventI {
  int getCommitIdx();

  @Nullable
  int[] getWireEnds();

  @Nullable
  int[] getCommitsEnds();

  int[] getCommitsStarts();

  // no parent commit present in quantity or exists
  boolean isEnd();

  boolean isStart();
  int getWaitStartsNumber();
  int[] getFutureWireStarts();
}
