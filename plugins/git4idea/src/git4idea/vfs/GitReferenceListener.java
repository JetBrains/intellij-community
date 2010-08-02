/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.vfs;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.EventListener;

/**
 * The listener for reference changes
 */
public interface GitReferenceListener extends EventListener {
  /**
   * This method is invoked when some references changed. The events do not describe which ones.
   *
   * @param root the root for which references might have changed, null if all roots might have been affected
   */
  void referencesChanged(VirtualFile root);
}
