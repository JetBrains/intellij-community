import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

class Test {
    private IntegerProperty newName = new SimpleIntegerProperty(this, "count");

    public int getNewName() {
        return newName.get();
    }

    public IntegerProperty newNameProperty() {
        return newName;
    }

    public void setNewName(int newName) {
        this.newName.set(newName);
    }
}