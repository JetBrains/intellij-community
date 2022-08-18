# Code Completion Evaluation Plugin

## Approach

The plugin deals with the code completion evaluation based on artificial queries. General approach:
1. Collect tokens to be completed for selected files.
2. For each token: delete it, call completion and save the result variants.
4. Calculate quality and performance metrics and present results in HTML-report.

It's not only about numerical value of quality. HTML-reports contain examples of source code with completion performed, so you can see how the completion works in specific situations.

## Installation

1. In Intellij IDEA add custom plugin repository `https://buildserver.labs.intellij.net/guestAuth/repository/download/ijplatform_IntelliJProjectDependencies_CodeCompletionProjects_CompletionEvaluation_Build/lastSuccessful/updatePlugins.xml`. [Instruction](https://www.jetbrains.com/help/idea/managing-plugins.html#repos)
2. Install plugin `Code Completion Evaluation` in Marketplace.

## Usage
- Full. Evaluation for multiple files with HTML-report as result.
  1. Select files and/or directories for code completion evaluation.
  2. Right click and select `Evaluate Completion For Selected Files`.
  3. Select strategy for actions generation in the opened dialog.
  4. After generation and interpretation of actions you will be asked to open the report in the browser.
- Quick. Evaluation for some element (all its content) in code with highlighting of completed tokens in editor.
  1. Right click on the element you want to complete and select `Evaluate Completion Here`.
  2. Select strategy for actions generation in the opened dialog.
  3. After generation and interpretation of actions tokens will be highlighted and provide information about completions by click.
- Compare multiple evaluations.
  1. Evaluate completion on different algorithms/strategies multiple times.
  2. Change `evaluationTitle` in `config.json` in corresponding workspaces. Results will group by this field in HTML-report.
  3. Select these workspaces.
  4. Right click and select `Generate Report By Selected Evaluations`.
  5. After report building you will be asked to open it in the browser.

## Features

- Completion types:
  - Basic
  - Smart
  - ML (basic with reordering based on machine learning)
- Strategies of actions generation:
  - Context of completion token (previous and all)
  - Prefix of completion token (empty, first characters and uppercase characters). Also, it can emulate typing
  - Type of completion tokens (method calls, variables, static members, etc.)
  - "Code Golf" Emulate user writing file with completion and count amount of moves (call completion, navigate between suggestions,
    typing character if completion didn't fit)
- Metrics:
  - Found@1
  - Found@5
  - Recall
  - Mean Rank
  - Latency
- HTML-reports:
  - Global report with metrics and links to file reports
  - Reports for files with all completions
- Headless mode


### Code Golf
Main idea: estimate how many actions it takes in average to write a line using the code completion.

At worst, we are not using code completion and creating file by typing char-by-char, let it be *Moves Count Normalised* 100% case.
With completion, we can save some moves by typing multiple chars at once and lower total actions.
So, lower *Moves Count* means faster creating file and better completion quality.

#### Process
The options are:
1. Select completion if it fits (our next input starts with code completion's suggestion)
2. Select only first token from completion if it fits (same as previous, but using only first token from suggestion.
   If completion contains only one token, it's equal to standard code completion)
3. Type next character if there are no suitable suggestions in code completion didn't

#### Metrics
Code golf has individual metrics:
- **Moves Count**: Count total amount of moves for writing current file
- **Moves Count Normalised** (in percent): Amount, based on non-completion number, in the worst case code completion never helped us
  and file was created only by typing characters
- **Perfect Line**: We count a session as perfect line, if we used completion's suggestion for typing more than half of line (>50%)
- Std metrics: Max Latency, Mean Latency and Sessions (each session is one line in the file)

#### Code golf moves
We summarize 3 types of actions:
- Call code completion (1 point)
- Choice suggestion from completion or symbol (if there is no offer in completion) (1 point)
- Navigation to the suggestion (if it fits) (N points, based on suggestion index, assuming first index is 0)

Moves = 0% is the best scenario, every line was completed from start to end with first suggestion in list
Moves > 100% is possible, when navigation in completion takes too many moves
> first indentation will be skipped up to any meaningful char

#### Settings
- **Top N** (default: -1): We can select only from N top filtered by the source (if there is) suggestions.
  Sometimes it's easier to type one more character and then navigate to suggestion in completion.
  *Pass -1 to disable*
- **Check Line** (default: true): Enable/Disable standard `Enter`completion.
- **Check Token** (default: true): Enable/Disable completion with first token. Such completion has lower priority then full (line) completion.
- **Source** (default: null): Pick only suggestion from a certain source, for ex. pick only `Full line` suggestions.
  *Pass null to disable*
- Std filters: It's possible to filter sessions by applying strategy's filters, such as: `METHOD_CALL`, `FIELD`, `VARIABLE`

## Headless Mode

You can run completion quality evaluation without IDEA UI.

### Usage

To start the evaluation in the headless mode you should describe where the project to evaluate is placed and rules for evaluation (language, strategy, output directories, etc.). We use JSON file for such king of description. The easiest way to create config is using `Create Config` button in settings dialog in UI mode of plugin. Here is an example of such file with description for possible options.
```json5
{
  "projectPath": "", // string with path to idea project
  "language": "Java",
  "outputDir": "", // string with path to output directory
  "actions": { // part of config about actions generation step
    "evaluationRoots": [ ], // list of string with paths to files/directories for evaluation
    "strategy": { // describes evaluation rules
      "completionGolf": false, // turn on "Code Golf" mode
      "context": "ALL", // ALL, PREVIOUS
      "prefix": { // policy how to complete particular token
        "name": "SimplePrefix", // SimplePrefix (type 1 or more letters), CapitalizePrefix or NoPrefix
        "emulateTyping": false, // type token char by char and save intermediate results
        "n": 1 // numbers of char to type before trigger completion
      },
      "filters": { // set of filters that allow to filter some completion locations out
        "statementTypes": [ // possible values: METHOD_CALL, FIELD, VARIABLE, TYPE_REFERENCE, ARGUMENT_NAME
          "METHOD_CALL"
        ],
        "isStatic": true, // null / true / false
        "packageRegex": ".*" // regex to check  if java package of resulting token is suitable for evaluation
      }
    }
  },
  "interpret": { // part of config about actions interpretation step
    "completionGolfSettings": {
      "topN": 5, // Take only N top suggestions, applying after filtering by source. Pass -1 to disable
      "checkLine": true, // Check if expected line starts with suggestion from completion
      "checkToken": true, // In case first token in suggestion equals to first token in expected string, we can pick only first token from suggestion. Suitable for full line or multiple token completions
      "source": "INTELLIJ" // Take suggestions, with specific source. Pass null to disable filter, possible values: [null, STANDARD, CODOTA, TAB_NINE, INTELLIJ]
    },
    "completionType": "BASIC", // BASIC, SMART, ML
    "completeTokenProbability": 1.0, // probability that token will be completed
    "completeTokenSeed": null, // seed for random (for previous option)
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
    ]
  }
}
```

Example of `config.json` to evaluate code completion on several modules from intellij-community project
```json5
{
  "projectPath": "PATH_TO_COMMUNITY_PROJECT",
  "language": "Java",
  "outputDir": "PATH_TO_COMMUNITY_PROJECT/completion-evaluation",
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
    ],
    "strategy": {
      "context": "ALL",
      "prefix": {
        "name": "SimplePrefix",
        "emulateTyping": false,
        "n": 1
      },
      "filters": {
        "statementTypes": [
          "METHOD_CALL"
        ],
        "isStatic": null,
        "packageRegex": ".*"
      }
    }
  },
  "interpret": {
    "completionType": "BASIC",
    "completeTokenProbability": 1.0,
    "completeTokenSeed": null,
    "saveLogs": false,
    "trainTestSplit": 70
  },
  "reports": {
    "evaluationTitle": "Basic",
    "sessionsFilters": []
  }
}
```

There are several options for the plugin to work in headless mode:
- Full. Use the config to execute the plugin on a set of files / directories. As a result of execution, HTML report will be created.
  - Usage: `ml-evaluate full [PATH_TO_CONFIG]`
  - If `PATH_TO_CONFIG` missing, default config will be created.
  - If config missing, default config will be created. Fill settings in default config before restarting evaluation.
- Generating actions. Allow only to find suitable locations to complete without evaluation.
  Generated actions can be reused later in `custom` mode.
  - Usage: `ml-evaluate actions [PATH_TO_CONFIG]`
- Custom. Allows you to interpret actions and/or generate reports on an existing workspace.
  - Usage: `ml-evaluate custom [--interpret-actions | -i] [--generate-report | -r] PATH_TO_WORKSPACE`
- Multiple Evaluations. Create a report based on multiple evaluations.
  - Usage: `ml-evaluate multiple-evaluations PATH_TO_WORKSPACE...`
- Multiple Evaluations in Directory. Works as the previous option to all workspaces in the directory.
  - Usage: `ml-evaluate compare-in PATH_TO_DIRECTORY`

There are many ways to start the evaluation in headless mode. Some of them are listed below.

#### Run from command line:
1. Add `-Djava.awt.headless=true` to jvm-options. [Instruction](https://www.jetbrains.com/help/idea/tuning-the-ide.html).
2. Create command line launcher for Intellij IDEA. [Instruction](https://www.jetbrains.com/help/idea/working-with-the-ide-features-from-command-line.html).
3. Run command `<Intellij IDEA> ml-evaluate OPTION OPTION_ARGS` with corresponding option.

#### Run with intellij from sources:
1. Create debug-configuration (copy from `IDEA` and add required options):
   ![run-configuration](https://user-images.githubusercontent.com/7608535/61994170-ef155a80-b07f-11e9-9a5b-fbfba5008875.png)
2. Start the configuration.

#### Evaluation framework on TeamCity
We have a set of [build configurations](https://buildserver.labs.intellij.net/project.html?projectId=ijplatform_IntelliJProjectDependencies_CodeCompletionProjects_CompletionEvaluation&tab=projectOverview
) on TeamCity based of evaluation-plugin project.
Most of them are devoted to estimating quality of code completion in different languages and products.

On top level there are few configurations: [Build](https://buildserver.labs.intellij.net/viewType.html?buildTypeId=ijplatform_IntelliJProjectDependencies_CodeCompletionProjects_CompletionEvaluation_Build)
(compiles the plugin)
and [Test](https://buildserver.labs.intellij.net/viewType.html?buildTypeId=ijplatform_IntelliJProjectDependencies_CodeCompletionProjects_CompletionEvaluation_Test)
(checks everything still work).
Below there are bunch of language-specific projects - [Java](https://buildserver.labs.intellij.net/project.html?projectId=ijplatform_IntelliJProjectDependencies_CodeCompletionProjects_CompletionEvaluation_Java&tab=projectOverview), Python, Kotlin, etc.
Each of these projects contains a set of build configurations.
They can be split on three groups:
* `Evaluate (ML/Basic) *` - takes the latest build of IDE/plugin and starts the evaluation process.
  Usually takes 30 - 120 minutes.
* `Compare ML and basic *` - takes output of corresponding Evaluate * builds and creates
  a comparison report (see build artifacts).
* `Generate logs *` - takes nightly IDE build, latest evaluation plugin build and starts evaluation.
  During the evaluation it collects the same logs we send from users.
  These logs can be fed into [ML Pipeline](https://buildserver.labs.intellij.net/project.html?projectId=ijplatform_IntelliJProjectDependencies_CodeCompletionProjects_MlPipeline&tab=projectOverview) project.

#### Q&A

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
