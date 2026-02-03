
/**
 * Hello darling
 * @author DarkWing Duck
 */
class testClass

/**
 * Hello darling outher
 * @author Morgana Macawber
 */
class outherClass {
    /**
     * Hello darling inner
     * @author Launchpad McQuack
     */
    inner class innerClass {

    }
}

// RENDER: <div class='content'><p style='margin-top:0;padding-top:0;'>Hello darling</p></div><table class='sections'><tr><td valign='top' class='section'><p>Authors:</td><td valign='top'>DarkWing Duck</td></table>
// RENDER: <div class='content'><p style='margin-top:0;padding-top:0;'>Hello darling outher</p></div><table class='sections'><tr><td valign='top' class='section'><p>Authors:</td><td valign='top'>Morgana Macawber</td></table>
// RENDER: <div class='content'><p style='margin-top:0;padding-top:0;'>Hello darling inner</p></div><table class='sections'><tr><td valign='top' class='section'><p>Authors:</td><td valign='top'>Launchpad McQuack</td></table>