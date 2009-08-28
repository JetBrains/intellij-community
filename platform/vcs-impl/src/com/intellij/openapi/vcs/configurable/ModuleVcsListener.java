package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.vcs.AbstractVcs;

import java.util.Collection;

/**
 * @author yole
 */
interface ModuleVcsListener {
  void activeVcsSetChanged(Collection<AbstractVcs> activeVcses);
}