// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

/**
 * author: lesya
 */
public interface TransactionProvider {
  void startTransaction(Object parameters);

  void commitTransaction(Object parameters) throws VcsException;

  void rollbackTransaction(Object parameters);
}
