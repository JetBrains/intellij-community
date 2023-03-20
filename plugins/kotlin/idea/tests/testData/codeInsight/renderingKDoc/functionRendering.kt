
/**
 * Hello darling
 * @author DarkWing Duck
 */
fun testFun() {}

class outherClass {
    /**
     * Hello darling instance
     * @author Morgana Macawber
     */
    fun instanceFun() {
        /**
         * Hello darling local
         * @author Launchpad McQuack
         */
        fun localFun() {
            if (true) {
                /**
                 * Hello darling superLocal
                 * @author Reginald Bushroot
                 */
                fun superLocalFun() {

                }
            }

        }
    }
}

// RENDER: <div class='content'><p style='margin-top:0;padding-top:0;'>Hello darling</p></div><table class='sections'><tr><td valign='top' class='section'><p>Authors:</td><td valign='top'>DarkWing Duck</td></table>
// RENDER: <div class='content'><p style='margin-top:0;padding-top:0;'>Hello darling instance</p></div><table class='sections'><tr><td valign='top' class='section'><p>Authors:</td><td valign='top'>Morgana Macawber</td></table>
// RENDER: <div class='content'><p style='margin-top:0;padding-top:0;'>Hello darling local</p></div><table class='sections'><tr><td valign='top' class='section'><p>Authors:</td><td valign='top'>Launchpad McQuack</td></table>
// RENDER: <div class='content'><p style='margin-top:0;padding-top:0;'>Hello darling superLocal</p></div><table class='sections'><tr><td valign='top' class='section'><p>Authors:</td><td valign='top'>Reginald Bushroot</td></table>