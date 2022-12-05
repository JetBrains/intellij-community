## How to run Completion Golf locally

There's set of run/debug configurations in `Application/Machine Learning` folder. 
This instruction explain how to start Completion golf to evaluate quality of 
Full Line model/plugin on existing project emulating user's behavior.  

Basic configuration:
1. Clone `fl-pipeline` [repo](https://jetbrains.team/p/ccrm/repositories/fl-pipeline/) to `ml-eval/fl-pipeline` directory 
on the same level as Intellij repo.
2. Open it with PyCharm and configure virtual env in `ml-eval/fl-pipeline/venv`.
3. Start `Application/Machine Learning/[full-line] Completion Golf for Python` run/debug configuration.
4. Wait until it finishes.
5. Check its output in `REPO_ROOT/bin/ml-eval-output/$DATE-$TIME`
6. To check HTML-report open in browser `$DATE-TIME/reports/WITHOUT COMPARISON/html/ALL/index.html`

Advanced configuration:
   * Completion golf configuration is stored in `$FULL_LINE_PLUGIN_DIR/resources/code-golf/dev/python.json`
   * To configure custom eval. project use `projectPath` and specify files/directories to use in `actions/evaluationRoots`
   * Configure `EVALUATION_PYTHON` env. variable in the run/debug configuration to use custom python3 executable/environment.

## Troubleshooting
* If you can't explain the results it's always possible to open evaluation 
project with IDEA/PyCharm with UI and check that the environment is properly configured.

