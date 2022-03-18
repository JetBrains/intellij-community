/**
 * | left  | center | right | default |
 * | :---- | :----: | ----: | ------- |
 * | 1     | 2      | 3     | 4       |
 *
 *
 * | foo | bar | baz |
 * | --- | --- | --- |
 * | 1   | 2   |
 * | 3   | 4   | 5   | 6 |
 *
 * | header | only |
 * | ------ | ---- |
 */
fun <caret>testFun() = 12

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">testFun</span>()<span style="">: </span><span style="color:#000000;">Int</span></pre></div><div class='content'><table><thead><tr><th align="left">left</th><th align="center"> center</th><th align="right"> right</th><th> default</th></tr></thead><tbody><tr><td align="left"> 1</td><td align="center"> 2</td><td align="right"> 3</td><td> 4</td></tr></tbody></table>   <table><thead><tr><th> foo</th><th> bar</th><th> baz</th></tr></thead><tbody><tr><td> 1</td><td> 2</td></tr><tr><td> 3</td><td> 4</td><td> 5</td></tr></tbody></table>  <table><thead><tr><th> header</th><th> only</th></tr></thead></table></div><table class='sections'></table><div class='bottom'><icon src="/org/jetbrains/kotlin/idea/icons/kotlin_file.svg"/>&nbsp;OnFunctionDeclarationWithGFMTable.kt<br/></div>
