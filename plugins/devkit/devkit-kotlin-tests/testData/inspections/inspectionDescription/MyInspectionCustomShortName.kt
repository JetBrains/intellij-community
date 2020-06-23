class <warning descr="Inspection does not have a description [getShortName()]">MyInspectionCustomShortName</warning> : com.intellij.codeInspection.InspectionProfileEntry() {
  override fun getShortName(): String {
    return "NOT_EXISTING_CUSTOM_SHORT_NAME"
  }
}
