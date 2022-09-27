# How to Contribute

## How to run

Use shared `IDEA` run configuration to compile and run IDEA for certain languages from sources.

## TeamCity project

We have project on TeamCity to run tests and deploy changes. Feel free to explore it [here](https://buildserver.labs.intellij.net/project/ijplatform_IntelliJProjectDependencies_JavaCompletionRanking_FullLineCodeCompletion?mode=trends)

Tests configurations: 
[ide-plugin](https://buildserver.labs.intellij.net/buildConfiguration/ijplatform_IntelliJProjectDependencies_JavaCompletionRanking_FullLineCodeCompletion_BuildPlugin?mode=builds),
[completion-server](https://buildserver.labs.intellij.net/buildConfiguration/ijplatform_IntelliJProjectDependencies_JavaCompletionRanking_FullLineCodeCompletion_CompletionServer?mode=builds)

Infrastructure Tests can be found [here](https://buildserver.labs.intellij.net/project/ijplatform_IntelliJProjectDependencies_JavaCompletionRanking_FullLineCodeCompletion_MlCompletionServer_InfrastructureTests?mode=trends).
They are required for deploying new server to [IntelliJ ML](https://buildserver.labs.intellij.net/project/ItD_IJML_Flcc?mode=trends).
Contact Kirill Krylov or Vladislav Tankov for deployment. 

## Branches

Please keep correct branch names according to which module you are contributing for correct CI:

- **completion-server** - `completion-server/*`
- **ide-plugin** - `ide-plugin/*`
- **infrastructure-tests** - `infrastructure-tests/*`

## Code reviews
All submissions, require review. We use Space merge requests for this purpose.

---
If you have any question please feel free to open an issue on [YouTrack](https://youtrack.jetbrains.com/issues/ML)
or open a [MR](https://jetbrains.team/p/ccrm) so we can work on improving it!

Please send your feedback to [ML Code Completion](https://jetbrains.team/team?team=Machine%20Learning%20in%20Code%20Completion-UdZB01b2voX) team_
