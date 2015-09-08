package com.intellij.usages.impl.rules;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.usages.UsageGroup;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class UsageGroupBase implements UsageGroup {
  private final int myOrder;

  protected UsageGroupBase(int order) {
    myOrder = order;
  }

  @Override
  public void update() {
  }

  @Nullable
  @Override
  public FileStatus getFileStatus() {
    return null;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public Icon getIcon(boolean isOpen) {
    return null;
  }

  @Override
  public void navigate(boolean focus) {
  }

  @Override
  public boolean canNavigate() {
    return false;
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }

  @Override
  public int compareTo(UsageGroup o) {
    if (!(o instanceof UsageGroupBase)) {
      return -1;
    }
    return Comparing.compare(myOrder, ((UsageGroupBase)o).myOrder);
  }
}
