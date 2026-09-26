class SimpleDataBinder {

  void doBind(<caret>Object obj, Object errors) {
    setPropertyValue obj, preprocessValue(null)
  }

  def preprocessValue(propertyValue) {
    propertyValue
  }

  def setPropertyValue(obj,  propertyValue) {}
}