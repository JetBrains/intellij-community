// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import git4idea.i18n.GitBundle

class UnsupportedWSLVersionException : GitVersionIdentificationException(
  GitBundle.message("git.executable.validation.error.wsl.start.title"),
  null
)