// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit.signing

import com.intellij.openapi.vcs.VcsException
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls

internal open class GpgAgentConfigException(message: @Nls String, cause: Throwable?) : VcsException(message, cause)

internal class ReadGpgAgentConfigException(cause: Throwable?) :
  GpgAgentConfigException(GitBundle.message("gpg.pinentry.agent.configuration.read.exception"), cause)
internal class GpgPinentryProgramDetectionException(cause: Throwable?) :
  GpgAgentConfigException(GitBundle.message("gpg.pinentry.agent.configuration.detect.default.pinentry.exception"), cause)
internal class GenerateLauncherException(cause: Throwable) :
  GpgAgentConfigException(GitBundle.message("gpg.pinentry.agent.configuration.launcher.generation.exception"), cause)
internal class BackupGpgAgentConfigException(cause: Throwable) :
  GpgAgentConfigException(GitBundle.message("gpg.pinentry.agent.configuration.configuration.backup.exception"), cause)
internal class SaveGpgAgentConfigException(cause: Throwable) :
  GpgAgentConfigException(GitBundle.message("gpg.pinentry.agent.configuration.save.exception"), cause)