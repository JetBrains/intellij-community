/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.test;

/**
 * To be used for testing.
 */
public class Owner {

    private String firstName;
    private String lastName;
    private String street1;
    private String street2;
    private String zip;
    private String phone;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getStreet1() {
        return street1;
    }

    public void setStreet1(String street1) {
        this.street1 = street1;
    }

    public String getStreet2() {
        return street2;
    }

    public void setStreet2(String street2) {
        this.street2 = street2;
    }

    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Owner owner = (Owner) o;

        if (firstName != null ? !firstName.equals(owner.firstName) : owner.firstName != null) return false;
        if (lastName != null ? !lastName.equals(owner.lastName) : owner.lastName != null) return false;
        if (phone != null ? !phone.equals(owner.phone) : owner.phone != null) return false;
        if (street1 != null ? !street1.equals(owner.street1) : owner.street1 != null) return false;
        if (street2 != null ? !street2.equals(owner.street2) : owner.street2 != null) return false;
        if (zip != null ? !zip.equals(owner.zip) : owner.zip != null) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = (firstName != null ? firstName.hashCode() : 0);
        result = 29 * result + (lastName != null ? lastName.hashCode() : 0);
        result = 29 * result + (street1 != null ? street1.hashCode() : 0);
        result = 29 * result + (street2 != null ? street2.hashCode() : 0);
        result = 29 * result + (zip != null ? zip.hashCode() : 0);
        result = 29 * result + (phone != null ? phone.hashCode() : 0);
        return result;
    }

    public void kill() {
        // kill it
    }

}
