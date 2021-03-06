class MyWithDescriptionCustomShortNameInspection : com.intellij.codeInspection.InspectionProfileEntry() {
    override fun getShortName(): String {
      return "customShortName"
    }
}