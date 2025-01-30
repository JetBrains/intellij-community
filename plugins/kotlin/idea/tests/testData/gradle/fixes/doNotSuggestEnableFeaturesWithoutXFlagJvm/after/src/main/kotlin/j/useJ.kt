// "Configure arguments for the feature: synthetic java properties" "false"
// K2_ERROR: The feature "references to synthetic java properties" is only available since language version 2.2
package j

private fun useJ() {
    Customer::name
}
