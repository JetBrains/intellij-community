// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.performanceTesting;

import com.jetbrains.performancePlugin.CommandProvider;
import com.jetbrains.performancePlugin.CreateCommand;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class GitCommandProvider implements CommandProvider {
  @Override
  public @NotNull Map<String, CreateCommand> getCommands() {
    return Map.ofEntries(
      Map.entry(GitCheckoutCommand.PREFIX, GitCheckoutCommand::new),
      Map.entry(ShowFileHistoryCommand.PREFIX, ShowFileHistoryCommand::new),
      Map.entry(GitCommitCommand.PREFIX, GitCommitCommand::new),
      Map.entry(FilterVcsLogTabCommand.PREFIX, FilterVcsLogTabCommand::new),
      Map.entry(GitShowVcsWidgetCommand.PREFIX, GitShowVcsWidgetCommand::new),
      Map.entry(ShowFileAnnotationCommand.PREFIX, ShowFileAnnotationCommand::new),
      Map.entry(CheckGitLogIndexedCommand.PREFIX, CheckGitLogIndexedCommand::new),
      Map.entry(WaitVcsLogIndexingCommand.PREFIX, WaitVcsLogIndexingCommand::new),
      Map.entry(WaitForVcsLogUpdateCommand.PREFIX, WaitForVcsLogUpdateCommand::new),
      Map.entry(GitRollbackCommand.PREFIX, GitRollbackCommand::new)
    );
  }
}
