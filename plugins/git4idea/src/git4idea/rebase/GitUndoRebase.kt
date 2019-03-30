// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

internal sealed class UndoPossibility {
  object Possible : UndoPossibility()
  object HeadMoved : UndoPossibility()
  class PushedToProtectedBranch(val branch: String) : UndoPossibility()
  object Error : UndoPossibility()
}

