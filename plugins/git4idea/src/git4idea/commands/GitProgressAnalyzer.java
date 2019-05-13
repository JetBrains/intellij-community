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
package git4idea.commands;

/**
 * Use to update the progress indicator with the current operation progress.
 * See {@link GitStandardProgressAnalyzer} which should be suitable for most git tasks.
 */
public interface GitProgressAnalyzer {
  /**
   * Analyzes the git process output line and returns the value of the progress indicator.
   * @param output git process output line (e.g. "Receiving objects: 67%).
   * @return completed fraction of the progress, or -1 if the line doesn't mean anything to the analyzer.
   */
  double analyzeProgress(String output);
}
