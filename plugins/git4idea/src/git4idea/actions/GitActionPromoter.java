// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.openapi.actionSystem.ActionPromoter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

public class GitActionPromoter implements ActionPromoter {
  @Override
  public List<AnAction> promote(List<AnAction> actions, DataContext context) {
    return ContainerUtil.findAll(actions, action -> action instanceof GitAdd);
  }
}
