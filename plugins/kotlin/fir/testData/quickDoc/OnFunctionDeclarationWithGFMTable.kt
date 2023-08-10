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

//INFO: <div class='definition'><pre>fun testFun(): Int</pre></div><div class='content'><table><thead><tr><th align="left"> left</th><th align="center"> center</th><th align="right"> right</th><th> default</th></tr></thead><tbody><tr><td align="left"> 1</td><td align="center"> 2</td><td align="right"> 3</td><td> 4</td></tr></tbody></table>   <table><thead><tr><th> foo</th><th> bar</th><th> baz</th></tr></thead><tbody><tr><td> 1</td><td> 2</td></tr><tr><td> 3</td><td> 4</td><td> 5</td></tr></tbody></table>  <table><thead><tr><th> header</th><th> only</th></tr></thead></table></div><table class='sections'></table><div class='bottom'><icon src="file"/>&nbsp;OnFunctionDeclarationWithGFMTable.kt<br/></div>
