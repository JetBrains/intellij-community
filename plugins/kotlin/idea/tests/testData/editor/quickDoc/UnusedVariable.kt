fun action(transform: (String) -> Int) {}

fun test() {
    action { <caret>_ -> 0 }
}

//INFO: <div class='definition'><pre><span style="color:#000000;">_</span><span style="">:</span> <span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span></pre></div>
