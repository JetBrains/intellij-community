int missing() {
  InputStream i = null

  try {
    return 1;
  }
  catch(Exception e) {

  }
<warning descr="Not all execution paths return a value">}</warning>

int ok() {
  InputStream i = null

  try {
    return 1;
  }
  finally {
    try {
      i.close()
    }
    catch (Exception ignored) {

    }
  }
}