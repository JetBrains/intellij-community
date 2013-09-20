package org.hanuna.gitalk.ui;


import com.intellij.vcs.log.VcsRef;

public interface RefAction {
  void perform(VcsRef ref);
}
