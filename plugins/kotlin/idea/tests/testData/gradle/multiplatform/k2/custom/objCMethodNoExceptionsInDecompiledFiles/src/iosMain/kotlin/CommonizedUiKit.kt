// Issue: KTIJ-30257

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationMeta

internal fun baz(uam: UIApplicationMeta) {
    uam.ne<caret>w()
}
