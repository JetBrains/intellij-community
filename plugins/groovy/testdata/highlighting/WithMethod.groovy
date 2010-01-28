final def calendar = Calendar.getInstance()

calendar.with {
  clear()
  set YEAR, 2009
  set MONTH, 7
  set DAY_OF_MONTH, 30
  println "Time is ${time}"
}