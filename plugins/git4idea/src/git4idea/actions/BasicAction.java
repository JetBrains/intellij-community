// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.openapi.fileEditor.FileDocumentManager;

/**
 * @deprecated Use {@link FileDocumentManager#saveAllDocuments()} directly.
 */
@Deprecated
public abstract class BasicAction {

  /**
   * @deprecated Use {@link FileDocumentManager#saveAllDocuments()} directly.
   */
  @Deprecated
  public static void saveAll() {
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
