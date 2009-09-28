Boolean getSchedule() {
  String cronExpression = ""
  // "Groovy Truth" Empty strings and null strings evaluate to false
  if (cronExpression) {
    return false
  } else {
    return true
  }
}

Boolean getSchedule2() {
  String cronExpression = ""
  // "Groovy Truth" Empty strings and null strings evaluate to false
  return cronExpression ? true : false
}

boolean getSchedule3(boolean cronExpression) {
  return <warning descr="'cronExpression ? true : false' can be simplified to 'cronExpression'">cronExpression ? true : false</warning>
}

boolean getSchedule4(Boolean cronExpression) {
  return <warning descr="'cronExpression ? true : false' can be simplified to 'cronExpression'">cronExpression ? true : false</warning>
}



