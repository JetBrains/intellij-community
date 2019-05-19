class C {

  {
    new CsvParserSettings() {
        @Override
        protected CsvParserSettings clone() throws CloneNotSupportedException {
            throw new CloneNotSupportedException();
        }
    };
  }

}

class CsvParserSettings implements Cloneable { }