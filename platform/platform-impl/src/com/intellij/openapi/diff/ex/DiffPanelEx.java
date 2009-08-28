/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 31, 2002
 * Time: 3:02:52 PM
 * To change template for new interface use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.diff.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.editor.Editor;

public interface DiffPanelEx extends DiffPanel, Disposable {
  Editor getEditor1();
  Editor getEditor2();

  DiffPanelOptions getOptions();

  void setComparisonPolicy(ComparisonPolicy comparisonPolicy);

  ComparisonPolicy getComparisonPolicy();
}
