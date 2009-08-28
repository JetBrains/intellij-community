package com.intellij.openapi.vcs;

import com.intellij.ide.errorTreeView.HotfixData;

import java.util.List;
import java.util.Map;

public interface VcsExceptionsHotFixer {
  Map<HotfixData, List<VcsException>> groupExceptions(final ActionType type, final List<VcsException> exceptions);
}
