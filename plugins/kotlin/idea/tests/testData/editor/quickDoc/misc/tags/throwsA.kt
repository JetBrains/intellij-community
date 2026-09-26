/**
 * Pre-throws.
 *
 * @throws ThrownA
 * @exception quick.doc.ThrownB comment
 * @exception [ThrownC] several words
 * @throws [quick.doc.ThrownD] line 0
 * line 1
 * @throws ThrownContainerA.ThrownE line 0
 *
 * line 2
 * @exception [quick.doc.ThrownContainerA.ThrownF]
 */
fun <caret>throwsA() {}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">throwsA</span>()<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Pre-throws.</p></div><table class='sections'><tr><td valign='top' class='section'><p>Throws:</td><td valign='top'><p><code><a href="psi_element://ThrownA"><code><span style="color:#0000ff;">ThrownA</span></code></a></code><p><code><a href="psi_element://quick.doc.ThrownD"><code><span style="color:#000000;">quick</span><span style="">.</span><span style="color:#000000;">doc</span><span style="">.</span><span style="color:#0000ff;">ThrownD</span></code></a></code> - line 0 line 1<p><code><a href="psi_element://ThrownContainerA.ThrownE"><code><span style="color:#000000;">ThrownContainerA</span><span style="">.</span><span style="color:#0000ff;">ThrownE</span></code></a></code> - <p style='margin-top:0;padding-top:0;'>line 0</p>
//INFO: <p>line 2</p><p><code><a href="psi_element://quick.doc.ThrownB"><code><span style="color:#000000;">quick</span><span style="">.</span><span style="color:#000000;">doc</span><span style="">.</span><span style="color:#0000ff;">ThrownB</span></code></a></code> - comment<p><code><a href="psi_element://ThrownC"><code><span style="color:#0000ff;">ThrownC</span></code></a></code> - several words<p><code><a href="psi_element://quick.doc.ThrownContainerA.ThrownF"><code><span style="color:#000000;">quick</span><span style="">.</span><span style="color:#000000;">doc</span><span style="">.</span><span style="color:#000000;">ThrownContainerA</span><span style="">.</span><span style="color:#0000ff;">ThrownF</span></code></a></code></td></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;throwsA.kt<br/></div>
