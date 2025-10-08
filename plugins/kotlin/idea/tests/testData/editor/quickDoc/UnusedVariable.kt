fun action(transform: (String) -> Int) {}

fun test() {
    action { <caret>_ -> 0 }
}
// IGNORE_K1
//INFO: <div class='definition'><pre><span style="color:#808080;font-style:italic;">unused var</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span></pre></div>
