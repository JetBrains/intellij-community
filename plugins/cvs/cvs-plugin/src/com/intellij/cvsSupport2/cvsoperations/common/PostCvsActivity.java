package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.cvsoperations.cvsUpdate.MergedWithConflictProjectOrModuleFile;

/**
 * author: lesya
 */
public interface PostCvsActivity {
  PostCvsActivity DEAF = new PostCvsActivity() {
    public void registerCorruptedProjectOrModuleFile(MergedWithConflictProjectOrModuleFile mergedWithConflictProjectOrModuleFile) {
    }

  };

  void registerCorruptedProjectOrModuleFile(MergedWithConflictProjectOrModuleFile mergedWithConflictProjectOrModuleFile);

}
