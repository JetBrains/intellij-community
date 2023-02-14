// !RENDER_DIAGNOSTICS_MESSAGES

package foo

expect class <!AMBIGUOUS_ACTUALS("Class 'ActualInMiddleCompatibleInBottom'; bottom, middle"), LINE_MARKER("descr='Has actuals in middle module'")!>ActualInMiddleCompatibleInBottom<!>
expect class <!AMBIGUOUS_ACTUALS("Class 'CompatibleInMiddleActualInBottom'; bottom, middle"), LINE_MARKER("descr='Has actuals in bottom module'")!>CompatibleInMiddleActualInBottom<!>

expect class <!AMBIGUOUS_ACTUALS("Class 'CompatibleInMiddleAndBottom'; bottom, middle")!>CompatibleInMiddleAndBottom<!>
