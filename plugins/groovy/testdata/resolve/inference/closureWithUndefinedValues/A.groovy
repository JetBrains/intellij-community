def getCrmCountryOptions = { def token ->
  return searchResponse
}

def getCrmCountryOptionId = { def token ->
  def match = getCrmCountryOptions(token).find { def o -> false }
  def someValue = match.get("Value")
  return someValue
}.memoize()

getCrmCount<ref>ryOptionId