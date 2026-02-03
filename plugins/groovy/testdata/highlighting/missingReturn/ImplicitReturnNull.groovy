def jetbrains = {
  if (a) {
    return void
  }
}

def method = {
  try {
    // do something
  } catch (NumberFormatException e) {
    // do something
    return redirect(action: 'index')
  }
  sessionFactory.currentSession.flush()
  redirect(action: 'anotherMethod')
}

def method2() {
  try {
    // do something
  } catch (NumberFormatException e) {
    // do something
    return redirect(action: 'index')
  }
  sessionFactory.currentSession.flush()
  redirect(action: 'anotherMethod')
}