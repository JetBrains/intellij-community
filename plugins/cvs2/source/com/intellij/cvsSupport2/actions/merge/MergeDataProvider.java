package com.intellij.cvsSupport2.actions.merge;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeData;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Jul 14, 2005
 * Time: 5:53:07 PM
 * To change this template use File | Settings | File Templates.
 */

interface MergeDataProvider {
  @NotNull
  MergeData createData() throws VcsException;
}
