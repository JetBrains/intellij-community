class SimpleDataBinder {

  void doBind(<caret>obj, errors) {
    setPropertyValue obj, preprocessValue(null)
  }

  def preprocessValue(propertyValue) {
    propertyValue
  }

  def setPropertyValue(obj,  propertyValue) {}
}