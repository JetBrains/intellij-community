# Evaluation Plugin

## Approach

The plugin deals with the evaluation of IDE features based on artificial queries. General approach:
1. Find places in the source code where to invoke the feature.
2. For each such place prepare the context, invoke the feature and save the results.
3. Calculate quality and performance metrics and present results in HTML-report.

It's not only about numerical value of quality.
HTML-reports contain examples of source code with the results of invocation, so you can see how the feature works in specific situations.

## Installation

1. In Intellij IDEA add custom plugin repository `https://buildserver.labs.intellij.net/guestAuth/repository/download/ijplatform_IntelliJProjectDependencies_CodeCompletionProjects_CompletionEvaluation_BuildFromIdea/lastSuccessful/updatePlugins.xml`. [Instruction](https://www.jetbrains.com/help/idea/managing-plugins.html#repos)
2. Install plugin `Evaluation Plugin` in Marketplace.

## Supported features

- **token-completion**:
  - evaluation of default completion engine by calling completion at fixed positions in code tokens
  - strategy (settings for the feature in the config):
```json5
{
  "context": "ALL", // ALL, PREVIOUS
  "prefix": {
    "name": "SimplePrefix", // SimplePrefix (type 1 or more letters), CapitalizePrefix or NoPrefix
    "n": 1
  },
  "filters": { // set of filters that allow to filter some completion locations out
    "statementTypes": [ // possible values: METHOD_CALL, FIELD, VARIABLE, TYPE_REFERENCE, ARGUMENT_NAME
      "METHOD_CALL"
    ],
    "isStatic": true, // null / true / false
    "packageRegex": ".*" // regex to check  if java package of resulting token is suitable for evaluation
  }
}
```
- **line-completion**:
  - similar to token completion but takes into account full line proposals with specific metrics and reports for this case
  - strategy:
```json5
{
  "mode": "TOKENS", // call completion only in meaningful tokens or everywhere; possible values: TOKENS, ALL
  "invokeOnEachChar": true, // close popup after unsuccessful completion and invoke again (only for line-completion-golf feature)
  "topN": 5, // take only N top proposals, applying after filtering by source
  "checkLine": true, // accept multi token proposals
  "source": "INTELLIJ", // take suggestions, with specific source; possible values: INTELLIJ (full-line), TAB_NINE, CODOTA
  "suggestionsProvider": "DEFAULT" // provider of proposals (DEFAULT - completion engine), can be extended
}
```
  - you can use `pathToModelZip` to use custom ranking model for the completions (do not pass `source` in this case to use the suggestions from all contributors)
- **line-completion-golf**:
  - also takes into account full line proposals but tries to write the entire file from the beginning using completion (instead of calling at fixed positions).
  - strategy the same as for line-completion
- **rename**:
  - evaluation of rename refactoring IDE feature by calling on existing variables or other identifiers
  - strategy:
```json5
{
  "placeholderName": "DUMMY", // identifier for renaming existing variables
  "suggestionsProvider": "DEFAULT", // provider of proposals (DEFAULT - IDE refactoring engine, LLM-rename - proposals of LLM plugin), can be extended
  "filters": {
    "statementTypes": null // currently not supported
  }
}
```

## Metrics

- Recall@K
- Precision
- Mean Rank
- Prefix Similarity
- Edit Distance
- Latency
- etc

You can find descriptions of all metrics in the code (`com.intellij.cce.metric.Metric.getDescription`).

Most of the metrics are also described [here](https://jetbrains.team/p/ccrm/documents/Full-Line-Code-Completion/a/Completion-Benchmark-ex-Golf-Metrics).

## Usage

The plugin works in the headless mode of IDE.
To start the evaluation you should describe where the project to evaluate is placed and rules for evaluation (language, strategy, output directories, etc.).
We use JSON file for such king of description.
Here is an example of such file with description for possible options but the strategy block depends on the feature used for evaluation.
```json5
{
  "projectPath": "", // string with path to idea project
  "language": "Java",
  "outputDir": "", // string with path to output directory
  "strategy": { // describes parameters of evaluation - depends on the feature (example below is for token-completion)
    "context": "ALL",
    "prefix": {
      "name": "SimplePrefix",
      "n": 1
    },
    "filters": {
      "statementTypes": [
        "METHOD_CALL"
      ],
      "isStatic": true,
      "packageRegex": ".*"
    }
  },
  "actions": { // part of config about actions generation step
    "evaluationRoots": [], // list of string with paths to files/directories for evaluation
  },
  "interpret": { // part of config about actions interpretation step
    "sessionProbability": 1.0, // probability that session won't be skipped
    "sessionSeed": null, // seed for random (for previous option)
    "saveLogs": false, // save completion logs or not (only if stats-collector plugin installed)
    "logsTrainingPercentage": 70 // percentage for logs separation on training/validate
  },
  "reports": { // part of config about report generation step
    "evaluationTitle": "Basic", // header name in HTML-report (use different names for report generation on multiple evaluations)
    "sessionsFilters": [ // create multiple reports corresponding to these sessions filters (filter "All" creates by default)
      {
        "name": "Static method calls only",
        "filters": {
          "statementTypes": [
            "METHOD_CALL"
          ],
          "isStatic": true,
          "packageRegex": ".*"
        }
      }
    ],
    "comparisonFilters": []
  }
}
```

Example of `config.json` to evaluate code completion on several modules from intellij-community project
```json5
{
  "projectPath": "PATH_TO_COMMUNITY_PROJECT",
  "language": "Java",
  "outputDir": "PATH_TO_COMMUNITY_PROJECT/completion-evaluation",
  "strategy": {
    "type": "BASIC",
    "context": "ALL",
    "prefix": {
      "name": "SimplePrefix",
      "n": 1
    },
    "filters": {
      "statementTypes": [
        "METHOD_CALL"
      ],
      "isStatic": null,
      "packageRegex": ".*"
    }
  },
  "actions": {
    "evaluationRoots": [
      "java/java-indexing-impl",
      "java/java-analysis-impl",
      "platform/analysis-impl",
      "platform/core-impl",
      "platform/indexing-impl",
      "platform/vcs-impl",
      "platform/xdebugger-impl",
      "plugins/git4idea",
      "plugins/java-decompiler",
      "plugins/gradle",
      "plugins/markdown",
      "plugins/sh",
      "plugins/terminal",
      "plugins/yaml"
    ]
  },
  "interpret": {
    "experimentGroup": null,
    "sessionProbability": 1.0,
    "sessionSeed": null,
    "saveLogs": false,
    "saveFeatures": false,
    "logLocationAndItemText": false,
    "trainTestSplit": 70
  },
  "reports": {
    "evaluationTitle": "Basic",
    "sessionsFilters": [],
    "comparisonFilters": []
  }
}
```

There are several options for the running plugin:
- Full. Use the config to execute the plugin on a set of files / directories. As a result of execution, HTML report will be created.
  - Usage: `ml-evaluate full FEATURE_NAME [PATH_TO_CONFIG]`
  - If `PATH_TO_CONFIG` missing, default config will be created.
  - If config missing, default config will be created. Fill settings in default config before restarting evaluation.
- Generating actions. Allow only to find suitable locations to complete without evaluation.
  Generated actions can be reused later in `custom` mode.
  - Usage: `ml-evaluate actions FEATURE_NAME [PATH_TO_CONFIG]`
- Custom. Allows you to interpret actions and/or generate reports on an existing workspace.
  - Usage: `ml-evaluate custom FEATURE_NAME [--interpret-actions | -i] [--generate-report | -r] PATH_TO_WORKSPACE`
- Multiple Evaluations. Create a report based on multiple evaluations.
  - Usage: `ml-evaluate multiple-evaluations FEATURE_NAME PATH_TO_WORKSPACE...`
- Multiple Evaluations in Directory. Works as the previous option to all workspaces in the directory.
  - Usage: `ml-evaluate compare-in FEATURE_NAME PATH_TO_DIRECTORY`

There are many ways to start the evaluation in headless mode. Some of them are listed below.

#### Run with intellij from sources:
- Use an existing run-configuration for `line-completion` feature among `Machine Learning/[full-line] Completion Evaluation for <Language>`
- Create a new run-configuration (copy from `IDEA` or another IDE) add required options:
  1. `-Djava.awt.headless=true` to jvm-options
  2. `ml-evaluate OPTION FEATURE_NAME OPTION_ARGS` to cli arguments

#### Run from command line:
1. Add `-Djava.awt.headless=true` to jvm-options. [Instruction](https://www.jetbrains.com/help/idea/tuning-the-ide.html).
2. Create command line launcher for Intellij IDEA. [Instruction](https://www.jetbrains.com/help/idea/working-with-the-ide-features-from-command-line.html).
3. Run command `<Intellij IDEA> ml-evaluate OPTION FEATURE_NAME OPTION_ARGS` with corresponding option and feature.

#### Evaluation framework on TeamCity
We have a set of [build configurations](https://buildserver.labs.intellij.net/project.html?projectId=ijplatform_IntelliJProjectDependencies_CodeCompletionProjects_CompletionEvaluation&tab=projectOverview
) on TeamCity based of evaluation-plugin project.
Most of them are devoted to estimating quality of code completion in different languages and products.

On top level there are few configurations: [Build](https://buildserver.labs.intellij.net/viewType.html?buildTypeId=ijplatform_IntelliJProjectDependencies_CodeCompletionProjects_CompletionEvaluation_Build)
(compiles the plugin)
and [Test](https://buildserver.labs.intellij.net/viewType.html?buildTypeId=ijplatform_IntelliJProjectDependencies_CodeCompletionProjects_CompletionEvaluation_Test)
(checks everything still work).
Below there is a bunch of language-specific projects - [Java](https://buildserver.labs.intellij.net/project.html?projectId=ijplatform_IntelliJProjectDependencies_CodeCompletionProjects_CompletionEvaluation_Java&tab=projectOverview), Python, Kotlin, etc.
Each of these projects contains a set of build configurations.
They can be split on three groups:
* `Evaluate (ML/Basic) *` - takes the latest build of IDE/plugin and starts the evaluation process.
  Usually takes 30 - 120 minutes.
* `Compare ML and basic *` - takes output of corresponding "Evaluate * builds" and creates
  a comparison report (see build artifacts).
* `Generate logs *` - takes nightly IDE build, latest evaluation plugin build and starts evaluation.
  During the evaluation it collects the same logs we send from users.
  These logs can be fed into [ML Pipeline](https://buildserver.labs.intellij.net/project.html?projectId=ijplatform_IntelliJProjectDependencies_CodeCompletionProjects_MlPipeline&tab=projectOverview) project.

## Q&A

Q: How can I compare default completion quality vs ML?

A: Run `Evaluate ML *` and `Evaluate Basic *` configurations (perhaps, simultaneously).
After they finish just start the corresponding `Compare ML and Basic *` configuration.

---

Q: I implemented collecting for a new feature into completion logs.
How can I check if the feature is collected and has any impact on completion quality?

A: Start `Generate logs *` configuration. Once it finished, start `Build * model` in [ML Pipeline](https://buildserver.labs.intellij.net/project.html?projectId=ijplatform_IntelliJProjectDependencies_CodeCompletionProjects_MlPipeline&tab=projectOverview) project.

---

Q: I want the similar reports for a new language.

A: Contact Alexey Kalina. The main challenge here is to set up SDK and project to evaluate on in the headless mode.
If you can DIY we can provide assistance where to add that.

---

Q: I want to compare quality with a specific parameters but cannot find a suitable build configuration. What can I do?

A: Contact Alexey Kalina or Vitaliy Bibaev.
