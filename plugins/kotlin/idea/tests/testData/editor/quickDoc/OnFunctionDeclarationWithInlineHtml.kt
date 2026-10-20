/**
 * The value is <b>about</b> 42, see <code>answer</code> and <em>this</em> note.
 * It returns a List<String>, and <script>alert(1)</script> is escaped.
 * The area is π<sup>2</sup> and water is H<sub>2</sub>O.
 * A quoted attribute in <script title="<b>">alert(1)</script> is escaped too.
 */
fun <caret>testFun() = 12

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">testFun</span>()<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>The value is <b>about</b> 42, see <code>answer</code> and <i>this</i> note. It returns a List&lt;String&gt;, and &lt;script&gt;alert(1)&lt;/script&gt; is escaped. The area is π<sup>2</sup> and water is H<sub>2</sub>O. A quoted attribute in &lt;script title=&quot;&lt;b&gt;&quot;&gt;alert(1)&lt;/script&gt; is escaped too.</p></div><table class='sections'></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;OnFunctionDeclarationWithInlineHtml.kt<br/></div>
