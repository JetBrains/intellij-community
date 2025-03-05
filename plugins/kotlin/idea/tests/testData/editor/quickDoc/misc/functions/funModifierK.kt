tailrec fun <caret>funModifierK(p: Int, s: Int): Int = if (p <= 1) s else funModifierK(p - 1, p * s)
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">tailrec</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">funModifierK</span>(
//INFO:     <span style="color:#000000;">p</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span>,
//INFO:     <span style="color:#000000;">s</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span>
//INFO: )<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;funModifierK.kt<br/></div>
