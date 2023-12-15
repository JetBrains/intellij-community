class A : OverrideMe() {
    override fun <caret>overrideMe() {
    }
}


//INFO: <div class='definition'><pre>override fun overrideMe(): Unit</pre></div></pre></div><table class='sections'><p><tr><td valign='top' class='section'><p>From class:</td><td valign='top'><p><a href="psi_element://OverrideMe"><code><span style="color:#000000;">OverrideMe</span></code></a><br>
//INFO:   Some comment
//INFO:      </td><tr><td valign='top' class='section'><p>Overrides:</td><td valign='top'><p><a href="psi_element://OverrideMe#overrideMe()"><code><span style="color:#000000;">overrideMe</span></code></a> in class <a href="psi_element://OverrideMe"><code><span style="color:#000000;">OverrideMe</span></code></a></td></table><div class='bottom'><icon src="class"/>&nbsp;<a href="psi_element://A"><code><span style="color:#000000;">A</span></code></a><br/></div>
