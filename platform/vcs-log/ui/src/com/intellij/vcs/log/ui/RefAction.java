package com.intellij.vcs.log.ui;


import com.intellij.vcs.log.VcsRef;

public interface RefAction {
  void perform(VcsRef ref);
}
